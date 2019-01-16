import { Component, OnInit } from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {Movie} from "../model/movie";
import {LoginService} from "../services/login.service";
import {Router} from "@angular/router";
import {constant} from "../model/constant";

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent implements OnInit {

  guessMovies: Movie[] = [];
  hotMovies: Movie[] = [];
  newMovies: Movie[] = [];
  rateMoreMovies: Movie[] = [];
  wishMovies: Movie[] = [];



  genres = [
    {
      name:"动作片",
      value:'Action',
      checked:false
    },{
      name:"冒险经历",
      value:'Adventure',
      checked:false
    },{
      name:"动画片",
      value:'Animation',
      checked:false
    },{
      name:"喜剧",
      value:'Comedy',
      checked:false
    },{
      name:"犯罪",
      value:'Crime',
      checked:false
    },{
      name:"纪录片",
      value:'Documentary',
      checked:false
    },{
      name:"喜剧文学",
      value:'Drama',
      checked:false
    },{
      name:"家庭片",
      value:'Family',
      checked:false
    },{
      name:"魔幻",
      value:'Fantasy',
      checked:false
    },{
      name:"外国片",
      value:'Foreign',
      checked:false
    },{
      name:"历史片",
      value:'History',
      checked:false
    },{
      name:"恐怖片",
      value:'Horror',
      checked:false
    },{
      name:"音乐片",
      value:'Music',
      checked:false
    },{
      name:"悬疑片",
      value:'Mystery',
      checked:false
    },{
      name:"爱情片",
      value:'Romance',
      checked:false
    },{
      name:"科幻片",
      value:'Science fiction',
      checked:false
    },{
      name:"电视电影",
      value:'Tv movie',
      checked:false
    },{
      name:"惊悚片",
      value:'Thriller',
      checked:false
    },{
      name:"战争片",
      value:'War',
      checked:false
    },{
      name:"西部片",
      value:'Western',
      checked:false
    }
  ]

  constructor(private httpService : HttpClient,private loginService:LoginService, private router:Router) {
  }

  ngOnInit(): void {
    this.getGuessMovies();
    this.getHotMovies();
    this.getNewMovies();
    this.getRateMoreMovies();
    this.getWishMovies();
  }

  updateGenres():void {
    var prefGenres = "";
    this.genres.map(x=>{
      if(x.checked){
        prefGenres = prefGenres + x.value +','
      }
    })
    prefGenres = prefGenres.slice(0,prefGenres.length-1)
    this.httpService
      .get(constant.BUSSINESS_SERVER_URL+'rest/users/pref?username='+this.loginService.user.username+"&genres="+prefGenres)
      .subscribe(
        data => {
          if(data['success'] == true){
            this.loginService.user.first = false;
            this.router.navigate(['/home']);
          }
        },
        err => {
          console.log('Something went wrong!');
        }
      );
  }

  isLoginFirst():boolean{
    return this.loginService.user.first
  }

  getGuessMovies():void{
    this.httpService
      .get(constant.BUSSINESS_SERVER_URL+'rest/movie/guess?num=6&username='+this.loginService.user.username)
      .subscribe(
        data => {
          if(data['success'] == true){
            this.guessMovies = data['movies'];
          }
        },
        err => {
          console.log('Somethi,g went wrong!');
        }
      );
  }
  getHotMovies():void{
    this.httpService
      .get(constant.BUSSINESS_SERVER_URL+'rest/movie/hot?num=6&username='+this.loginService.user.username)
      .subscribe(
        data => {
          if(data['success'] == true){

            this.hotMovies = data['movies'];
            /*
            angular.forEach(movies, function(data){
              var movie = new Movie;
              movie.id=data['mid'];
              movie.descri=data['descri'];
              movie.issue=data['issue'];
              movie.language=data['language'];
              movie.name=data['name'];
              movie.shoot=data['shoot'];
              movie.timelong=data['timelong'];
              this.hotMovies.push(movie);
            });*/
          }
        },
        err => {
          console.log('Somethi,g went wrong!');
        }
      );
  }
  getNewMovies():void{
    /*var movie = new Movie;
    movie.mid=1;
    movie.descri="Set in the 22nd century, The Matrix tells the story of a computer hacker who joins a group of underground insurgents fighting the vast and powerful computers who now rule the earth.";
    movie.issue="November 20, 2001";
    movie.language="English";
    movie.name="The Matrix";
    movie.picture="./assets/1.jpg";
    movie.score=8;
    movie.shoot="1999";
    movie.timelong="136 minutes";

    this.newMovies.push(movie);
    this.newMovies.push(movie);
    this.newMovies.push(movie);
    this.newMovies.push(movie);
    this.newMovies.push(movie);
    this.newMovies.push(movie);*/
    this.httpService
      .get(constant.BUSSINESS_SERVER_URL+'rest/movie/new?num=6&username='+this.loginService.user.username)
      .subscribe(
        data => {
          if(data['success'] == true){
            this.newMovies = data['movies'];
          }
        },
        err => {
          console.log('Something went wrong!');
        }
      );
  }
  getRateMoreMovies():void{
    this.httpService
      .get(constant.BUSSINESS_SERVER_URL+'rest/movie/rate?num=6&username='+this.loginService.user.username)
      .subscribe(
        data => {
          if(data['success'] == true){
            this.rateMoreMovies = data['movies'];
          }
        },
        err => {
          console.log('Somethi,g went wrong!');
        }
      );
  }
  getWishMovies():void{
    this.httpService
      .get(constant.BUSSINESS_SERVER_URL+'rest/movie/wish?num=6&username='+this.loginService.user.username)
      .subscribe(
        data => {
          if(data['success'] == true){
            this.wishMovies = data['movies'];
          }
        },
        err => {
          console.log('Somethi,g went wrong!');
        }
      );
  }
}
